function [ locs ] = detectPeaks( vals )
%UNTITLED2 Summary of this function goes here
%   Detailed explanation goes here

    locs = zeros(0,length(vals));
    loc_ind = 1;

    lmax = 0;
    lmaxI = 0;
    
    isIncreasing = false;
    
    for i=4 : length(vals)
        
        if(isIncreasing)
            if vals(i) > lmax
                lmax = vals(i);
                lmaxI = i;
            end
            if (vals(i) < vals(i-1)) && (vals(i-1) < vals(i-2))
                locs(loc_ind)=lmaxI;
                loc_ind = loc_ind+1;
                isIncreasing = false;
                lmax = -1000;

            end
        else
            if (vals(i) > vals(i-1)) && (vals(i-1) > vals(i-2))
                isIncreasing = true;
                lmax = vals(i);

            end
        end
        
    end

end

